package com.agcocorp.harvestergs.routing.loaders

import com.agcocorp.harvestergs.routing.Schema
import com.fasterxml.jackson.databind.ObjectMapper

class SwaggerSchemaMapper {
    private setInnerProp(obj, prop, value) {
        def current = obj
        def props = prop.tokenize('.')
        def last = props.size() - 1

        props.eachWithIndex {it, idx ->
            if (idx == last) {
                current[it] = value
            }
            else {
                if (current[it] == null) {
                    current[it] = [:]
                }
                current = current[it]
            }
        }
    }

    private setIfNotNull(obj, prop, value) {
        if (value) {
            setInnerProp obj, prop, value
        }
    }

    private setNotNull(obj, prop, value) {
        if (!value) {
            // todo: throw proper exception here
            throw new RuntimeException()
        }
        setInnerProp obj, prop, value
    }

    private mapRelationships(swagger, value) {
        setNotNull swagger, 'properties.relationships', [properties: [:]]
        def relationships = swagger.properties.relationships.properties
        value.each {
            def description = "Id reference to a ${it.value.type} object"
            relationships[it.key] = [ type: 'string', description: description.toString() ]
        }
    }

    private final uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

    def mapAttributes(swagger, value, level) {
        def attr
        if (level == 0) {
            setNotNull swagger, 'properties.attributes', [properties: [:]]
            // todo: test UUID pattern validation
            swagger.properties.id = [ type: 'string', pattern: uuidPattern ]
            attr = swagger.properties.attributes
        } else {
            swagger['properties'] = [:]
            attr = swagger
        }

        value.each {
            attr.properties[it.key] = mapToSwagger(it.value, level + 1)
        }
    }

    private mapProperties(value, swagger, name, level) {
        if (level > 0) {
            setIfNotNull swagger, "$name", value
        } else {
            setIfNotNull swagger, "properties.attributes.$name", value
        }
    }

    private mapToSwagger(parent, level = 0) {
        def swagger = [:]
        parent.each { name, value ->
            if (value) {
                switch (name) {
                    case 'relationships':
                        mapRelationships swagger, value
                        break;
                    case 'attributes':
                        mapAttributes swagger, value, level
                        break;
                    case 'items':
                        setIfNotNull swagger, 'items', mapToSwagger(value, level + 1)
                        break;
                    default:
                        mapProperties value, swagger, name, level
                        break;
                }
            }
        }

        swagger
    }

    def map(schema, type = null) {
        // todo: converting to a map removes awkward closure handling -- but aren't there any better ideas?
        def s = new ObjectMapper().convertValue(schema, Map.class)
        def swagger = [
            properties: [
                data: mapToSwagger(s)
            ]
        ]
        if (type) {
            setNotNull swagger, 'properties.data.properties.type.enum', [ type ]
        }
        return swagger
    }
}
